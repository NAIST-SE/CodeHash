
require 'ripper'
require 'git'
require 'digest/sha1'

=begin
    Code hash computation for Ruby files in git repositories
=end

=begin
   r = RubyCode.new(content) 
   r.code_hash  =>  SHA1 hash for tokens ignoring white space and comments
   r.token_count  => the number of tokens in the source code
=end
class RubyCode

	Ignored = [ :on_comment, :on_ignored_nl, :on_sp, :on_embdoc, :on_embdoc_beg, :on_embdoc_end ]
	
	def initialize(content)
		# Ripper returns a list of tokens for a file even if syntax errors exist
		begin
			tokens = Ripper.lex(content)
			if tokens.is_a? Array
				t = tokens.map { |t| 
					if Ignored.include? t[1]
						nil
					else
						t[2] + "\0"
					end
				}.compact
				@length = t.length
				# remove "\r" to ignore the differences of new line characters in tokens
				@normalized = t.join("").gsub("\r", "")
			else
				@length = 0
				@normalized = ""
			end
		rescue
			@length = 0
			@normalized = ""
		end
	end
	
	def code_hash
		return Digest::SHA1.hexdigest(@normalized)
	end
	
	def token_count
		@length
	end
	
end


class GitRubyFile

    # Open a git repository 
	def initialize(gitrepo)
		@git = Git.bare(gitrepo)
	end

    # Process a file in a git repository
	def parse_ruby(sha, lang)
		src = @git.object(sha)
		result = nil
		if src
			code = src.contents
			if code then
				c = RubyCode.new(code)
				if c.token_count > 0
					result = "#{sha}\t#{lang}\t#{c.code_hash}\t#{src.size}\t#{c.token_count}"
				end
			end
		end
		result
	end

    # Process an input file and generate an output file
	def process_filelist(filename, outputfile)
		File.open(outputfile, "w") { |out|
			File.open(filename) { |f|
				f.each_line { |line|
					line.chomp!
					sha1, name, lang = line.split("\t")
					if lang == "RUBY"
						out.puts parse_ruby(sha1, lang)
					end
				}
			}
		}
	end

end


File.open(ARGV.shift) { |f|
	f.each_line { |line|
		gitdir, filelist, outfile, mode = line.split(",")
		next if mode == "minhash"  # minhash is unsupported
		GitRubyFile.new(gitdir).process_filelist(filelist, outfile)
	}
}

